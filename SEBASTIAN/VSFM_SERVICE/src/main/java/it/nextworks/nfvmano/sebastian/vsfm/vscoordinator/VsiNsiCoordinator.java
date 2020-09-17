package it.nextworks.nfvmano.sebastian.vsfm.vscoordinator;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.nextworks.nfvmano.libs.ifa.common.enums.NsScaleType;
import it.nextworks.nfvmano.libs.ifa.common.exceptions.*;
import it.nextworks.nfvmano.libs.ifa.descriptors.nsd.ScaleNsToLevelData;
import it.nextworks.nfvmano.libs.ifa.osmanfvo.nslcm.interfaces.NsLcmProviderInterface;
import it.nextworks.nfvmano.libs.ifa.osmanfvo.nslcm.interfaces.elements.ScaleNsData;
import it.nextworks.nfvmano.libs.ifa.osmanfvo.nslcm.interfaces.messages.ScaleNsRequest;
import it.nextworks.nfvmano.sebastian.common.VsNssiAction;
import it.nextworks.nfvmano.sebastian.common.VsNssiActionType;

import it.nextworks.nfvmano.sebastian.nsmf.interfaces.NsmfLcmProviderInterface;
import it.nextworks.nfvmano.sebastian.nsmf.messages.ModifyNsiRequest;
import it.nextworks.nfvmano.sebastian.vsfm.VsLcmService;
import it.nextworks.nfvmano.sebastian.vsfm.engine.messages.CoordinateVsiNssiRequest;
import it.nextworks.nfvmano.sebastian.vsfm.engine.messages.VsmfEngineMessage;
import it.nextworks.nfvmano.sebastian.vsfm.engine.messages.VsmfEngineMessageType;
import it.nextworks.nfvmano.sebastian.vsfm.messages.TerminateVsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VsiNsiCoordinator {
    private static final Logger log = LoggerFactory.getLogger(VsiNsiCoordinator.class);
    private VsCoordinatorStatus internalStatus;


    private String networkSliceInstanceId;
    private String nstId;
    private String domainId;
    private List<String> registeredVsi = new ArrayList();
    private NsmfLcmProviderInterface nsLcmService;
    private String tenantId;
    private VsLcmService vsLcmService;



    public VsiNsiCoordinator(String networkSliceInstanceId, String nstId, String domainId, NsmfLcmProviderInterface nsLcmService, VsLcmService vsLcmService){
        this.networkSliceInstanceId= networkSliceInstanceId;
        this.internalStatus=VsCoordinatorStatus.READY;
        this.domainId=domainId;
        this.nsLcmService=nsLcmService;
        this.vsLcmService= vsLcmService;
    }
    /**
     * Method used to receive messages about VSIs to be updated/terminated from the Rabbit MQ
     *
     * @param message received message
     */
    public void receiveMessage(String message) {
        ObjectMapper mapper = new ObjectMapper();
        VsmfEngineMessage em = null;
        try {
            em = mapper.readValue(message, VsmfEngineMessage.class);
            VsmfEngineMessageType type = em.getType();
            switch (type) {


                case COORDINATE_VSI_NSSI_REQUEST: {

                    log.debug("Processing VSI coordination request.");
                    CoordinateVsiNssiRequest coordinateVsiNssiRequest = (CoordinateVsiNssiRequest) em;
                    processCoordinateVsiNssiRequest(coordinateVsiNssiRequest);
                    break;
                } default:
                    log.error("Received message with not supported type. Skipping.");
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    synchronized void processCoordinateVsiNssiRequest(CoordinateVsiNssiRequest msg){
        if (internalStatus != VsCoordinatorStatus.READY) {
            manageVsCoordinatorError("Received coordinate request in wrong status. Skipping message.");
            return;
        }

        VsNssiAction vsNssiAction = msg.getVsNssiAction();

        log.debug("Requesting vertical service network slice coordination: "+vsNssiAction.getActionType()+" as requested by "+msg.getVsiRequesterId());
        internalStatus = VsCoordinatorStatus.COORDINATION_IN_PROGRESS;


        switch (vsNssiAction.getActionType()){
            case MODIFY:
                log.debug("Triggering NSI Modify");


                try {
                    String dfId = vsNssiAction.getDfId();
                    String ilId = vsNssiAction.getIlId();
                    //The vsiId doesnot seem to be used.
                    ModifyNsiRequest modifyNsiRequest = new ModifyNsiRequest(networkSliceInstanceId, nstId, dfId, ilId, null );
                    nsLcmService.modifyNetworkSlice(modifyNsiRequest, domainId, tenantId);
                    log.debug("ScaleNsRequest triggered");
                } catch (MethodNotImplementedException e) {
                    manageVsCoordinatorError("Error during scale request", e);
                } catch (NotExistingEntityException e) {
                    manageVsCoordinatorError("Error during scale request", e);
                } catch (FailedOperationException e) {
                    manageVsCoordinatorError("Error during scale request", e);
                } catch (MalformattedElementException e) {
                    manageVsCoordinatorError("Error during scale request", e);
                } catch (NotPermittedOperationException e) {
                    manageVsCoordinatorError("Error during scale request", e);
                }
                break;
            default:
                manageVsCoordinatorError("Unsupported VS NSSI action type");
        }





    }

    private void manageVsCoordinatorError(String errorMessage){
        manageVsCoordinatorError(errorMessage, null);
    }

    private void manageVsCoordinatorError(String errorMessage, Exception e) {

        log.error(errorMessage,e);

        internalStatus=VsCoordinatorStatus.ERROR;

    }

}
