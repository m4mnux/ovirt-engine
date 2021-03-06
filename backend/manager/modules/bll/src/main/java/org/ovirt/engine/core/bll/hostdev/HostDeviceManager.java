package org.ovirt.engine.core.bll.hostdev;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.ovirt.engine.core.bll.LockMessagesMatchUtil;
import org.ovirt.engine.core.bll.interfaces.BackendInternal;
import org.ovirt.engine.core.bll.network.host.NetworkDeviceHelper;
import org.ovirt.engine.core.common.BackendService;
import org.ovirt.engine.core.common.action.ActionParametersBase;
import org.ovirt.engine.core.common.action.ActionType;
import org.ovirt.engine.core.common.action.VdsActionParameters;
import org.ovirt.engine.core.common.businessentities.HostDevice;
import org.ovirt.engine.core.common.businessentities.VDSStatus;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VmDevice;
import org.ovirt.engine.core.common.businessentities.VmDeviceGeneralType;
import org.ovirt.engine.core.common.errors.EngineMessage;
import org.ovirt.engine.core.common.locks.LockingGroup;
import org.ovirt.engine.core.common.queries.IdQueryParameters;
import org.ovirt.engine.core.common.queries.QueryType;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.common.utils.VmDeviceType;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.HostDeviceDao;
import org.ovirt.engine.core.dao.VdsDynamicDao;
import org.ovirt.engine.core.dao.VmDeviceDao;
import org.ovirt.engine.core.utils.lock.EngineLock;
import org.ovirt.engine.core.utils.lock.LockManager;

@ApplicationScoped
public class HostDeviceManager implements BackendService {

    @Inject
    private VdsDynamicDao hostDynamicDao;

    @Inject
    private LockManager lockManager;

    @Inject
    private HostDeviceDao hostDeviceDao;

    @Inject
    private VmDeviceDao vmDeviceDao;

    @Inject
    private BackendInternal backend;

    @Inject
    private NetworkDeviceHelper networkDeviceHelper;

    @PostConstruct
    private void init() {
        // It is sufficient to refresh only the devices of 'UP' hosts since other hosts
        // will have their devices refreshed in InitVdsOnUpCommand
        List<ActionParametersBase> parameters = hostDynamicDao.getIdsOfHostsWithStatus(VDSStatus.Up)
                .stream()
                .map(hostId -> new VdsActionParameters(hostId))
                .collect(Collectors.toList());

        backend.runInternalMultipleActions(ActionType.RefreshHostDevices, parameters);
        hostDeviceDao.cleanDownVms();
    }

    /**
     * Checks whether the specified VM is pinned to a host and has host devices directly attached to it
     *
     * @return true if the specified VM is pinned to a host and has host devices directly attached to it
     */
    public boolean checkVmNeedsDirectPassthrough(VM vm) {
        return vm.getDedicatedVmForVdsList().size() > 0 && checkVmNeedsDirectPassthrough(vm.getId());
    }

    /**
     * Checks whether one of host devices attached to given VM is of 'pci' capability.
     */
    public boolean checkVmNeedsPciDevices(Guid vmId) {
        return hostDeviceDao
                .getVmExtendedHostDevicesByVmId(vmId)
                .stream()
                .anyMatch(HostDevice::isPci);
    }

    private boolean checkVmNeedsDirectPassthrough(Guid vmId) {
        return vmDeviceDao.existsVmDeviceByVmIdAndType(vmId, VmDeviceGeneralType.HOSTDEV);
    }

    private boolean checkVmNeedsHostDevices(Guid vmId) {
        List<VmDevice> vfs = vmDeviceDao.getVmDeviceByVmIdTypeAndDevice(vmId,
                VmDeviceGeneralType.INTERFACE,
                VmDeviceType.HOST_DEVICE);

        return !vfs.isEmpty() || checkVmNeedsDirectPassthrough(vmId);
    }

    public boolean checkVmHostDeviceAvailability(VM vm, Guid vdsId) {
        if (!hostDeviceDao.checkVmHostDeviceAvailability(vm.getId(), vdsId)) {
            return false;
        }

        List<HostDevice> devices = backend.runInternalQuery(QueryType.GetExtendedVmHostDevicesByVmId,
                new IdQueryParameters(vm.getId())).getReturnValue();

        for (HostDevice device : devices) {
            if (!networkDeviceHelper.isDeviceNetworkFree(device)) {
                return false;
            }
        }

        return true;
    }

    public void allocateVmHostDevices(VM vm) {
        hostDeviceDao.markHostDevicesUsedByVmId(vm.getId(), vm.getDedicatedVmForVdsList().get(0));
    }

    public void freeVmHostDevices(Guid vmId) {
        hostDeviceDao.freeHostDevicesUsedByVmId(vmId);
    }

    public void acquireHostDevicesLock(Guid vdsId) {
        lockManager.acquireLockWait(new EngineLock(getExclusiveLockForHostDevices(vdsId)));
    }

    public void releaseHostDevicesLock(Guid vdsId) {
        lockManager.releaseLock(new EngineLock(getExclusiveLockForHostDevices(vdsId)));
    }

    private static Map<String, Pair<String, String>> getExclusiveLockForHostDevices(Guid vdsId) {
        return Collections.singletonMap(
                vdsId.toString(),
                LockMessagesMatchUtil.makeLockingPair(
                        LockingGroup.HOST_DEVICES,
                        EngineMessage.ACTION_TYPE_FAILED_OBJECT_LOCKED));
    }

    /**
     * Calls <code>ActionType.RefreshHost</code> on the specified host, in case any of the specified vms contain
     * host devices (that were attached directly or via the SRIOV scheduling)
     */
    public void refreshHostIfAnyVmHasHostDevices(Collection<Guid> vmIds, Guid hostId) {
        for (Guid vmId : vmIds) {
            if (checkVmNeedsHostDevices(vmId)) {
                backend.runInternalAction(ActionType.RefreshHost, new VdsActionParameters(hostId));
                return;
            }
        }
    }
}
