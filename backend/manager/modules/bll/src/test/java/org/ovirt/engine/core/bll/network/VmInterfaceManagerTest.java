package org.ovirt.engine.core.bll.network;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.transaction.TransactionManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.ovirt.engine.core.bll.context.NoOpCompensationContext;
import org.ovirt.engine.core.bll.network.macpool.MacPool;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.businessentities.network.VmNic;
import org.ovirt.engine.core.common.osinfo.OsRepository;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.compat.Version;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogDirector;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogable;
import org.ovirt.engine.core.dao.network.VmNetworkStatisticsDao;
import org.ovirt.engine.core.dao.network.VmNicDao;
import org.ovirt.engine.core.di.InjectorRule;
import org.ovirt.engine.core.utils.RandomUtils;

@RunWith(MockitoJUnitRunner.class)
public class VmInterfaceManagerTest {

    private static final int OS_ID = 0;
    private static final String VM_NAME = "vm name";

    @Rule
    public InjectorRule injectorRule = new InjectorRule();

    @Mock
    private MacPool macPool;

    @Mock
    private VmNetworkStatisticsDao vmNetworkStatisticsDao;

    @Mock
    private VmNicDao vmNicDao;

    @Mock
    private AuditLogDirector auditLogDirector;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TransactionManager transactionManager;

    @Captor
    private ArgumentCaptor<AuditLogable> auditLogableCaptor;

    private VmInterfaceManager vmInterfaceManager;

    @Mock
    private Version version;

    @Before
    @SuppressWarnings("unchecked")
    public void setupMocks() {
        vmInterfaceManager = spy(new VmInterfaceManager(macPool));
        doReturn(vmNetworkStatisticsDao).when(vmInterfaceManager).getVmNetworkStatisticsDao();
        doReturn(vmNicDao).when(vmInterfaceManager).getVmNicDao();
        doReturn(auditLogDirector).when(vmInterfaceManager).getAuditLogDirector();
        doNothing().when(vmInterfaceManager).removeFromExternalNetworks(anyList());

        injectorRule.bind(TransactionManager.class, transactionManager);
    }

    @Test
    public void add() {
        runAddAndVerify(createNewInterface(), OS_ID, true);
    }

    @Test
    public void addWithExistingMacAddressSucceed() {
        runAddAndVerify(createNewInterface(), OS_ID, false);
    }

    protected void runAddAndVerify(VmNic iface,
            int osId,
            boolean reassignMac) {

        OsRepository osRepository = mock(OsRepository.class);
        when(vmInterfaceManager.getOsRepository()).thenReturn(osRepository);
        when(osRepository.hasNicHotplugSupport(anyInt(), any(Version.class))).thenReturn(true);

        vmInterfaceManager.add(iface, NoOpCompensationContext.getInstance(), reassignMac, osId, version);
        if (!reassignMac) {
            verify(macPool).forceAddMac(iface.getMacAddress());
        } else {
            verify(macPool).allocateNewMac();
        }

        verify(vmNicDao).save(iface);
        verify(vmNetworkStatisticsDao).save(iface.getStatistics());
    }

    @Test
    public void removeAll() {
        List<VmNic> interfaces = Arrays.asList(createNewInterface(), createNewInterface());

        when(vmNicDao.getAllForVm(any(Guid.class))).thenReturn(interfaces);

        vmInterfaceManager.removeAll(Guid.newGuid());

        for (VmNic iface : interfaces) {
            verifyRemoveAllDelegatedCorrectly(iface);
        }
    }

    @Test
    public void testAuditLogMacInUse() {
        final VmNic iface = createNewInterface();

        vmInterfaceManager.auditLogMacInUse(iface);

        verifyCommonAuditLogFilledProperly(AuditLogType.MAC_ADDRESS_IS_IN_USE, iface);
    }

    @Test
    public void testAuditLogMacInUseUnplug() {
        final VmNic iface = createNewInterface();

        vmInterfaceManager.auditLogMacInUseUnplug(iface, VM_NAME);

        verifyCommonAuditLogFilledProperly(AuditLogType.MAC_ADDRESS_IS_IN_USE_UNPLUG, iface);
        assertEquals(auditLogableCaptor.getValue().getVmName(), VM_NAME);
    }

    private Map<String, String> verifyCommonAuditLogFilledProperly(AuditLogType auditLogType, VmNic iface) {
        verify(auditLogDirector).log(auditLogableCaptor.capture(), same(auditLogType));
        final Map<String, String> capturedCustomValues = auditLogableCaptor.getValue().getCustomValues();
        assertThat(capturedCustomValues, allOf(
                hasEntry("macaddr", iface.getMacAddress()),
                hasEntry("ifacename", iface.getName())));
        assertEquals(auditLogableCaptor.getValue().getVmId(), iface.getVmId());
        return capturedCustomValues;
    }

    /**
     * Verify that {@link VmInterfaceManager#removeAll} delegated correctly to {@link MacPool} & Daos.
     *
     * @param iface
     *            The interface to check for.
     */
    protected void verifyRemoveAllDelegatedCorrectly(VmNic iface) {
        verify(macPool, times(1)).freeMac(iface.getMacAddress());
        verify(vmNicDao).remove(iface.getId());
        verify(vmNetworkStatisticsDao).remove(iface.getId());
    }

    /**
     * @return A new interface that can be used in tests.
     */
    private static VmNic createNewInterface() {
        VmNic iface = new VmNic();
        iface.setId(Guid.newGuid());
        iface.setMacAddress(RandomUtils.instance().nextString(10));

        return iface;
    }
}
