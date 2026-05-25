using MassTransit;
using Microsoft.Extensions.Logging;
using Moq;
using Xunit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Tests;

public class SubscriptionConsumerTests
{
    private readonly Mock<INotificationDeliveryService> _deliveryMock = new();
    private readonly Mock<IDedupEventStore> _dedupMock = new();
    private readonly Mock<ILogger<SellerFrozenConsumer>> _loggerMock = new();

    private static Mock<ConsumeContext<T>> CreateContext<T>(T message) where T : class
    {
        var mock = new Mock<ConsumeContext<T>>();
        mock.Setup(x => x.Message).Returns(message);
        mock.Setup(x => x.MessageId).Returns(Guid.NewGuid());
        mock.Setup(x => x.CancellationToken).Returns(CancellationToken.None);
        return mock;
    }

    [Fact]
    public async Task SellerFrozenConsumer_CreatesNotificationWithCorrectFields()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);

        var consumer = new SellerFrozenConsumer(_deliveryMock.Object, _dedupMock.Object, _loggerMock.Object);
        var sellerId = Guid.NewGuid();
        var evt = new SellerFrozenEvent(sellerId, DateTimeOffset.UtcNow.AddDays(7), "Violation of terms");

        var contextMock = new Mock<ConsumeContext<SellerFrozenEvent>>();
        contextMock.Setup(x => x.Message).Returns(evt);
        contextMock.Setup(x => x.MessageId).Returns(Guid.NewGuid());
        contextMock.Setup(x => x.CancellationToken).Returns(CancellationToken.None);

        await consumer.Consume(contextMock.Object);

        _dedupMock.Verify(x => x.TryClaimAsync(
            It.IsAny<string>(), nameof(SellerFrozenConsumer), It.IsAny<CancellationToken>()), Times.Once);

        _deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == sellerId.ToString() &&
                n.Type == "SellerAccount" &&
                n.Title == "Account Frozen" &&
                n.Body.Contains("Violation of terms") &&
                n.Data["sellerId"] == sellerId.ToString() &&
                n.Data["reason"] == "Violation of terms"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task SellerFrozenConsumer_SkipsOnDedup()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(false);

        var consumer = new SellerFrozenConsumer(_deliveryMock.Object, _dedupMock.Object, _loggerMock.Object);
        var evt = new SellerFrozenEvent(Guid.NewGuid(), null, null);

        var contextMock = new Mock<ConsumeContext<SellerFrozenEvent>>();
        contextMock.Setup(x => x.Message).Returns(evt);
        contextMock.Setup(x => x.MessageId).Returns(Guid.NewGuid());
        contextMock.Setup(x => x.CancellationToken).Returns(CancellationToken.None);

        await consumer.Consume(contextMock.Object);

        _deliveryMock.Verify(x => x.DeliverAsync(It.IsAny<Notification>(), It.IsAny<CancellationToken>()), Times.Never);
    }

    [Fact]
    public async Task BundleActivatedConsumer_HasCorrectFieldMapping()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);

        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new BundleActivatedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<BundleActivatedConsumer>>().Object);

        var subId = Guid.NewGuid();
        var participantId = Guid.NewGuid();
        var sellerId = Guid.NewGuid();
        var evt = new BundleActivatedEvent(
            subId, participantId, sellerId, "Pro Plan", 2999L, "USD", 30);

        var contextMock = new Mock<ConsumeContext<BundleActivatedEvent>>();
        contextMock.Setup(x => x.Message).Returns(evt);
        contextMock.Setup(x => x.MessageId).Returns(Guid.NewGuid());
        contextMock.Setup(x => x.CancellationToken).Returns(CancellationToken.None);

        await consumer.Consume(contextMock.Object);

        // Verify that the new JSON property name attribute maps "id" to Id
        Assert.Equal(subId, evt.Id);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == participantId.ToString() &&
                n.Type == "BundleSubscription" &&
                n.Data["subscriptionId"] == subId.ToString() &&
                n.Data["sellerId"] == sellerId.ToString() &&
                n.Data["bundleName"] == "Pro Plan" &&
                n.Data["durationDays"] == "30"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task ChargebackProcessedConsumer_HasCorrectFieldMapping()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new ChargebackProcessedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<ChargebackProcessedConsumer>>().Object);

        var subId = Guid.NewGuid();
        var participantId = Guid.NewGuid();
        var evt = new ChargebackProcessedEvent(subId, participantId, 10000L, 2500L, true);
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == participantId.ToString() &&
                n.Type == "Chargeback" &&
                n.Data["chargebackCents"] == "2500" &&
                n.Data["lockedCents"] == "10000" &&
                n.Data["partial"] == "True"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task PaymentFailedConsumer_LogsWarningWhenNoRecipient()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);

        var loggerMock = new Mock<ILogger<PaymentFailedConsumer>>();
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new PaymentFailedConsumer(
            deliveryMock.Object, _dedupMock.Object, loggerMock.Object);

        var evt = new PaymentFailedEvent(null, null, 1, null);
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(It.IsAny<Notification>(), It.IsAny<CancellationToken>()), Times.Never);
        loggerMock.Verify(x => x.Log(
            LogLevel.Warning, It.IsAny<EventId>(),
            It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("no resolvable recipient")),
            It.IsAny<Exception>(), It.IsAny<Func<It.IsAnyType, Exception?, string>>()), Times.Once);
    }

    [Fact]
    public async Task PaymentFailedConsumer_DeliversToUserIdWhenPresent()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);

        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new PaymentFailedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<PaymentFailedConsumer>>().Object);

        var userId = Guid.NewGuid();
        var evt = new PaymentFailedEvent(userId, Guid.NewGuid(), 2, DateTimeOffset.UtcNow.AddDays(1));
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n => n.RecipientId == userId.ToString() && n.Data["attempt"] == "2"),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task PlanChangedConsumer_LogsWarningWhenNoUserId()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);

        var loggerMock = new Mock<ILogger<PlanChangedConsumer>>();
        var consumer = new PlanChangedConsumer(
            new Mock<INotificationDeliveryService>().Object, _dedupMock.Object, loggerMock.Object);

        var evt = new PlanChangedEvent(null, "Premium");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        loggerMock.Verify(x => x.Log(
            LogLevel.Warning, It.IsAny<EventId>(),
            It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("no UserId")),
            It.IsAny<Exception>(), It.IsAny<Func<It.IsAnyType, Exception?, string>>()), Times.Once);
    }

    [Fact]
    public async Task SellerClosedConsumer_HasCorrectFieldMapping()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);

        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new SellerClosedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<SellerClosedConsumer>>().Object);

        var sellerId = Guid.NewGuid();
        var evt = new SellerClosedEvent(sellerId, "Business closed", "seller");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == sellerId.ToString() &&
                n.Type == "SellerAccount" &&
                n.Title == "Account Closed"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task RenewalPromptConsumer_ContainsCorrectData()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);

        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new RenewalPromptConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<RenewalPromptConsumer>>().Object);

        var userId = Guid.NewGuid();
        var subId = Guid.NewGuid();
        var evt = new RenewalPromptEvent(userId, subId, null, 2999L, "USD", null);
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == userId.ToString() &&
                n.Type == "Renewal" &&
                n.Data["subscriptionId"] == subId.ToString() &&
                n.Data["amountCents"] == "2999" &&
                n.Data["currency"] == "USD"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }
}
