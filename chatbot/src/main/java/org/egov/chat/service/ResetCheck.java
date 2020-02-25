package org.egov.chat.service;

import lombok.extern.slf4j.Slf4j;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.egov.chat.config.KafkaStreamsConfig;
import org.egov.chat.models.EgovChat;
import org.egov.chat.models.egovchatserdes.EgovChatSerdes;
import org.egov.chat.repository.ConversationStateRepository;
import org.egov.chat.util.CommonAPIErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class ResetCheck {

    private String streamName = "reset-check";

    @Value("${flow.reset.keywords}")
    private String resetKeywordsString;

    private int fuzzymatchScoreThreshold = 90;

    @Autowired
    private KafkaStreamsConfig kafkaStreamsConfig;
    @Autowired
    private CommonAPIErrorMessage commonAPIErrorMessage;
    @Autowired
    private ConversationStateRepository conversationStateRepository;

    public void startStream(String inputTopic, String outputTopic) {

        Properties streamConfiguration = kafkaStreamsConfig.getDefaultStreamConfiguration();
        streamConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, streamName);
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, EgovChat> messagesKStream = builder.stream(inputTopic, Consumed.with(Serdes.String(),
                EgovChatSerdes.getSerde()));

        messagesKStream.flatMapValues(chatNode -> {
            try {
                chatNode.setResetConversation(isResetKeyword(chatNode));
                return Collections.singletonList(chatNode);
            } catch (Exception e) {
                log.error("error in reset check",e);
                commonAPIErrorMessage.resetFlowDuetoError(chatNode);
                return Collections.emptyList();
            }
        }).to(outputTopic, Produced.with(Serdes.String(), EgovChatSerdes.getSerde()));

        kafkaStreamsConfig.startStream(builder, streamConfiguration);

        log.info(streamName + " stream started");
    }

    private boolean isResetKeyword(EgovChat chatNode) {
        try {
            String answer = chatNode.getMessage().getRawInput();
            for (String resetKeyword : resetKeywordsString.split(",")) {
                int score = FuzzySearch.tokenSetRatio(resetKeyword, answer);
                if (score >= fuzzymatchScoreThreshold)
                    return true;
            }

            return false;
        } catch (Exception e) {
            log.error("error in reset check",e);
            return false;
        }
    }

}