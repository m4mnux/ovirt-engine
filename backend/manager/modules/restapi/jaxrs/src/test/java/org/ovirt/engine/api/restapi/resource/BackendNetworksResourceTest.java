package org.ovirt.engine.api.restapi.resource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.Test;
import org.ovirt.engine.api.model.DataCenter;
import org.ovirt.engine.api.model.Network;
import org.ovirt.engine.core.common.action.ActionType;
import org.ovirt.engine.core.common.action.AddNetworkStoragePoolParameters;
import org.ovirt.engine.core.common.businessentities.StoragePool;
import org.ovirt.engine.core.common.interfaces.SearchType;
import org.ovirt.engine.core.common.queries.IdQueryParameters;
import org.ovirt.engine.core.common.queries.NameQueryParameters;
import org.ovirt.engine.core.common.queries.QueryType;
import org.ovirt.engine.core.compat.Guid;

public class BackendNetworksResourceTest
        extends AbstractBackendNetworksResourceTest<BackendNetworksResource> {

    private static final Guid DATA_CENTER_ID = GUIDS[1];

    public BackendNetworksResourceTest() {
        super(new BackendNetworksResource(), SearchType.Network, "Networks : ");
    }

    @Test
    public void testAddNetwork() throws Exception {
        setUriInfo(setUpBasicUriExpectations());
        setUpCreationExpectations(ActionType.AddNetwork,
                                  AddNetworkStoragePoolParameters.class,
                                  new String[] { "StoragePoolId" },
                                  new Object[] { DATA_CENTER_ID },
                                  true,
                                  true,
                                  null, //GUIDS[0],
                                  QueryType.GetAllNetworks,
                                  IdQueryParameters.class,
                                  new String[] { "Id" },
                                  new Object[] { Guid.Empty },
                                  asList(getEntity(0)));
        Network model = getModel(0);
        model.setDataCenter(new DataCenter());
        model.getDataCenter().setId(DATA_CENTER_ID.toString());

        Response response = collection.add(model);
        assertEquals(201, response.getStatus());
        assertTrue(response.getEntity() instanceof Network);
        verifyModel((Network) response.getEntity(), 0);
    }

    @Test
    public void testAddNetworkWithNamedDataCenter() throws Exception {
        setUriInfo(setUpBasicUriExpectations());

        setUpEntityQueryExpectations(QueryType.GetStoragePoolByDatacenterName,
                NameQueryParameters.class,
                new String[] { "Name" },
                new Object[] { NAMES[1] },
                setUpStoragePool(DATA_CENTER_ID));

        setUpCreationExpectations(ActionType.AddNetwork,
                                  AddNetworkStoragePoolParameters.class,
                                  new String[] { "StoragePoolId" },
                                  new Object[] { DATA_CENTER_ID },
                                  true,
                                  true,
                                  null, //GUIDS[0],
                                  QueryType.GetAllNetworks,
                                  IdQueryParameters.class,
                                  new String[] { "Id" },
                                  new Object[] { Guid.Empty },
                                  asList(getEntity(0)));
        Network model = getModel(0);
        model.setDataCenter(new DataCenter());
        model.getDataCenter().setName(NAMES[1]);

        Response response = collection.add(model);
        assertEquals(201, response.getStatus());
        assertTrue(response.getEntity() instanceof Network);
        verifyModel((Network) response.getEntity(), 0);
    }

    @Test
    public void testAddNetworkCantDo() throws Exception {
        doTestBadAddNetwork(false, true, CANT_DO);
    }

    @Test
    public void testAddNetworkFailure() throws Exception {
        doTestBadAddNetwork(true, false, FAILURE);
    }

    private void doTestBadAddNetwork(boolean valid, boolean success, String detail) throws Exception {
        setUriInfo(setUpActionExpectations(ActionType.AddNetwork,
                                           AddNetworkStoragePoolParameters.class,
                                           new String[] { "StoragePoolId" },
                                           new Object[] { DATA_CENTER_ID },
                                           valid,
                                           success));
        Network model = getModel(0);
        model.setDataCenter(new DataCenter());
        model.getDataCenter().setId(DATA_CENTER_ID.toString());

        try {
            collection.add(model);
            fail("expected WebApplicationException");
        } catch (WebApplicationException wae) {
            verifyFault(wae, detail);
        }
    }

    @Test
    public void testAddIncompleteParameters() throws Exception {
        Network model = new Network();
        model.setName(NAMES[0]);
        setUriInfo(setUpBasicUriExpectations());
        try {
            collection.add(model);
            fail("expected WebApplicationException on incomplete parameters");
        } catch (WebApplicationException wae) {
             verifyIncompleteException(wae, "Network", "add", "dataCenter.name|id");
        }
    }

    @Test
    public void testQueryWithFilter() throws Exception {
        List<String> filterValue = new ArrayList<>();
        filterValue.add("true");
        reset(httpHeaders);
        when(httpHeaders.getRequestHeader(USER_FILTER_HEADER)).thenReturn(filterValue);
        setUpEntityQueryExpectations(1);
        setUriInfo(setUpBasicUriExpectations());
                List<Network> networks = getCollection();
        Collections.sort(networks, new NetworkIdComparator());
        verifyCollection(networks);
    }

    protected void setUpEntityQueryExpectations(int times, Object failure) throws Exception {
        while (times-- > 0) {
            setUpEntityQueryExpectations(QueryType.GetAllNetworks,
                                         IdQueryParameters.class,
                                         new String[] { "Id" },
                                         new Object[] { Guid.Empty },
                                         getEntityList(),
                                         failure);
        }
    }

    protected StoragePool setUpStoragePool(Guid id) {
        StoragePool pool = mock(StoragePool.class);
        when(pool.getId()).thenReturn(id);
        return pool;
    }

    class NetworkIdComparator implements Comparator<Network>{

        @Override
        public int compare(Network n1, Network n2) {
            if (n1.getId().equals(n2.getId())) {
                return 0;
            } else {
                return n1.getId().compareTo(n2.getId());
            }
        }
    }
}
