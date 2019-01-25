package com.redelastic.stocktrader.portfolio.impl;

import akka.Done;
import akka.NotUsed;
import akka.stream.javadsl.Flow;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.redelastic.stocktrader.broker.api.BrokerService;
import com.redelastic.stocktrader.broker.api.OrderResult;
import com.redelastic.stocktrader.broker.api.Trade;
import com.redelastic.stocktrader.order.OrderDetails;
import com.redelastic.stocktrader.portfolio.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class PortfolioServiceImpl implements PortfolioService {

    private final Logger log = LoggerFactory.getLogger(PortfolioServiceImpl.class);

    private final PortfolioRepository portfolioRepository;

    @Inject
    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                BrokerService brokerService) {
        this.portfolioRepository = portfolioRepository;

        // Listen for purchase order completions and send them to the corresponding portfolio
        brokerService.orderResults()
                .subscribe()
                .atLeastOnce(Flow.<OrderResult>create().mapAsync(1, this::handleOrderResult));
        // Note: Our order entity logic handles duplicate orderPlaced, hence at least once semantics work.
    }

    @Override
    public ServiceCall<OpenPortfolioDetails, String> openPortfolio() {
        return portfolioRepository::open;
    }

    @Override
    public ServiceCall<NotUsed, Done> liquidatePortfolio(String portfolioId) {
        return null;
    }

    @Override
    public ServiceCall<NotUsed, PortfolioView> getPortfolio(String portfolioId) {
        return notUsed ->
            portfolioRepository
                    .get(portfolioId)
                    .view();
    }

    @Override
    public ServiceCall<OrderDetails, String> placeOrder(String portfolioId) {
        return orderDetails -> {
            String orderId = UUID.randomUUID().toString();
            return portfolioRepository
                    .get(portfolioId)
                    .placeOrder(orderId, orderDetails)
                    .thenApply(done -> orderId);
        };
    }

    private CompletionStage<Done> handleOrderResult(OrderResult orderResult) {
        PortfolioModel portfolio = portfolioRepository.get(orderResult.getPortfolioId());
        if (orderResult instanceof OrderResult.OrderFulfilled) {
            Trade trade = ((OrderResult.OrderFulfilled) orderResult).getTrade();
            return portfolio.processTrade(trade);
        } else if (orderResult instanceof OrderResult.OrderFailed) {
            log.info(String.format("Order %s failed for portfolio %s.", orderResult.getOrderId(), orderResult.getPortfolioId()));
            return portfolio.orderFailed((OrderResult.OrderFailed)orderResult);
        } else {
            // TODO: handle order results other than completed
            return CompletableFuture.completedFuture(Done.getInstance());
        }
    }

    @Override
    public Topic<OrderPlaced> orderPlaced() {
        return TopicProducer.taggedStreamWithOffset(PortfolioEvent.TAG.allTags(), portfolioRepository::ordersStream);
    }
    

}
