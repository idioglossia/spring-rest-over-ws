package io.ep2p.row.server.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.ep2p.row.server.config.Naming;
import io.ep2p.row.server.context.DefaultContextImpl;
import io.ep2p.row.server.context.RowContextHolder;
import io.ep2p.row.server.context.RowUser;
import io.ep2p.row.server.domain.RowResponseStatus;
import io.ep2p.row.server.domain.protocol.RequestDto;
import io.ep2p.row.server.domain.protocol.ResponseDto;
import io.ep2p.row.server.filter.RowFilterChain;
import io.ep2p.row.server.ws.RowServerWebsocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.io.IOException;
import java.util.Set;

@Slf4j
public class ProtocolService {
    private final RowFilterChain rowFilterChain;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ProtocolService(RowFilterChain rowFilterChain) {
        this.rowFilterChain = rowFilterChain;
        objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    public boolean handle(RowServerWebsocket<?> rowServerWebsocket, TextMessage textMessage){
        log.trace("Received message: " + textMessage.getPayload());
        String payload = textMessage.getPayload();
        fillContext(rowServerWebsocket);
        ResponseDto responseDto = ResponseDto.builder().status(RowResponseStatus.OK.getId()).build();
        String requestId = null;
        try {
            RequestDto requestDto = objectMapper.readValue(payload, RequestDto.class);
            //ignoring payloads that are not request type
            if(requestDto.getType() == null || !requestDto.getType().equals("request")){
                log.trace("Payload type was not equal to \"request\" and might be a response for reused connection");
                return false;
            }
            requestId = requestDto.getId();
            Set<ConstraintViolation<RequestDto>> constraintViolations = validator.validate(requestDto);
            if(constraintViolations.size() > 0){
                responseDto = ResponseDto.builder()
                        .requestId(requestDto.getId())
                        .status(RowResponseStatus.PROTOCOL_ERROR.getId())
                        .build();
            }else {
                responseDto.setRequestId(requestId);
                rowFilterChain.filter(requestDto, responseDto, rowServerWebsocket);
            }
        } catch (JsonProcessingException e) {
            responseDto = ResponseDto.builder()
                    .status(RowResponseStatus.OTHER.getId())
                    .requestId(requestId)
                    .build();
            log.error("Json Error", e);
        } catch (Exception e) {
            responseDto.setStatus(RowResponseStatus.INTERNAL_SERVER_ERROR);
            log.error("Exception thrown while handling message", e);
        }

        try {
            String responsePayload = objectMapper.writeValueAsString(responseDto);
            log.trace("Sending response: "+ responsePayload);
            rowServerWebsocket.sendTextMessage(responsePayload);
        } catch (IOException e) {
            log.error("Failed to publish response to websocket", e);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    private void fillContext(RowServerWebsocket<?> rowServerWebsocket) {
        RowContextHolder.setContext(
                new DefaultContextImpl(
                        new RowUser((String) rowServerWebsocket.getAttributes().get(Naming.USER_ID_ATTRIBUTE_NAME), rowServerWebsocket.getId()), true
                )
        );
    }
}
