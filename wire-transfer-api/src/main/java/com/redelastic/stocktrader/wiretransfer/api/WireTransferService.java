package com.redelastic.stocktrader.wiretransfer.api;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.call;
import static com.lightbend.lagom.javadsl.api.Service.topic;

import akka.Done;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;


/**
 * We won't try to model arbitrary account to account transfers.
 */
public interface WireTransferService extends Service {

  ServiceCall<Transfer, Done> transferFunds();

  Topic<Transfer> completedTransfers();
  String PORTFOLIO_TRANSFER_TOPIC_ID = "WireTransfer-PortfolioTransfer";

  Topic<TransferRequest> transferRequests();

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("wire-transfer").withCalls(
            call(this::transferFunds)
        )
        .withTopics(
                topic(PORTFOLIO_TRANSFER_TOPIC_ID, this::completedTransfers)
        );
    // @formatter:on
  }
}
