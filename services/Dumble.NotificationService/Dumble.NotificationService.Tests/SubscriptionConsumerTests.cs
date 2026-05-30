using MassTransit;
using Microsoft.Extensions.Logging;
using Moq;
using Xunit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Constants;
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
                n.Type == NotificationTypes.SellerAccount &&
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
                n.Type == NotificationTypes.BundleSubscription &&
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
                n.Type == NotificationTypes.Chargeback &&
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
                n.Type == NotificationTypes.SellerAccount &&
                n.Title == "Account Closed"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task PaymentFailedFinalConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new PaymentFailedFinalConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<PaymentFailedFinalConsumer>>().Object);

        var userId = Guid.NewGuid();
        var evt = new PaymentFailedFinalEvent(userId, Guid.NewGuid(), 3);
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == userId.ToString() &&
                n.Type == NotificationTypes.PaymentIssue &&
                n.Title == "Payment Failed \u2014 Subscription Expired" &&
                n.Data["subscriptionId"] == evt.SubscriptionId.ToString()
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task BundleExpiredConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new BundleExpiredConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<BundleExpiredConsumer>>().Object);

        var subId = Guid.NewGuid();
        var evt = new BundleExpiredEvent(subId, "trial ended");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.Data["subscriptionId"] == subId.ToString() &&
                n.Data["reason"] == "trial ended" &&
                n.Type == NotificationTypes.BundleSubscription
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task RefundIssuedConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new RefundIssuedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<RefundIssuedConsumer>>().Object);

        var participantId = Guid.NewGuid();
        var evt = new RefundIssuedEvent(Guid.NewGuid(), participantId, 1500L);
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == participantId.ToString() &&
                n.Type == NotificationTypes.Refund &&
                n.Data["amountCents"] == "1500"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task SellerBannedConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new SellerBannedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<SellerBannedConsumer>>().Object);

        var sellerId = Guid.NewGuid();
        var evt = new SellerBannedEvent(sellerId, "Fraud");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == sellerId.ToString() &&
                n.Type == NotificationTypes.SellerAccount &&
                n.Title == "Account Banned" &&
                n.Data["reason"] == "Fraud"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task SellerWindingDownConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new SellerWindingDownConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<SellerWindingDownConsumer>>().Object);

        var sellerId = Guid.NewGuid();
        var evt = new SellerWindingDownEvent(sellerId, "Retiring");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == sellerId.ToString() &&
                n.Type == NotificationTypes.SellerAccount &&
                n.Data["reason"] == "Retiring"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task SellerUnfrozenConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new SellerUnfrozenConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<SellerUnfrozenConsumer>>().Object);

        var sellerId = Guid.NewGuid();
        var evt = new SellerUnfrozenEvent(sellerId);
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == sellerId.ToString() &&
                n.Type == NotificationTypes.SellerAccount &&
                n.Title == "Account Unfrozen"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task PlatformActivatedConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new PlatformActivatedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<PlatformActivatedConsumer>>().Object);

        var userId = Guid.NewGuid();
        var evt = new PlatformActivatedEvent(userId, "PREMIUM", "active");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == userId.ToString() &&
                n.Type == NotificationTypes.PlanChange &&
                n.Data["planCode"] == "PREMIUM"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task PlatformExpiredConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new PlatformExpiredConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<PlatformExpiredConsumer>>().Object);

        var userId = Guid.NewGuid();
        var evt = new PlatformExpiredEvent(userId, "PREMIUM");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == userId.ToString() &&
                n.Type == NotificationTypes.PlanChange
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task ReceiptIssuedConsumer_DeliversCorrectly()
    {
        _dedupMock.Setup(x => x.TryClaimAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(true);
        var deliveryMock = new Mock<INotificationDeliveryService>();
        var consumer = new ReceiptIssuedConsumer(
            deliveryMock.Object, _dedupMock.Object,
            new Mock<ILogger<ReceiptIssuedConsumer>>().Object);

        var participantId = Guid.NewGuid();
        var evt = new ReceiptIssuedEvent(Guid.NewGuid(), participantId, 2999L, "USD");
        var ctx = CreateContext(evt);

        await consumer.Consume(ctx.Object);

        deliveryMock.Verify(x => x.DeliverAsync(
            It.Is<Notification>(n =>
                n.RecipientId == participantId.ToString() &&
                n.Type == NotificationTypes.Receipt &&
                n.Data["amountCents"] == "2999"
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
                n.Type == NotificationTypes.Renewal &&
                n.Data["subscriptionId"] == subId.ToString() &&
                n.Data["amountCents"] == "2999" &&
                n.Data["currency"] == "USD"
            ),
            It.IsAny<CancellationToken>()), Times.Once);
    }
}
