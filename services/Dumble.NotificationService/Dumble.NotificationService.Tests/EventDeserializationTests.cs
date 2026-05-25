using System.Text.Json;
using Xunit;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Tests;

public class EventDeserializationTests
{
    [Fact]
    public void SellerFrozenEvent_DeserializesFromCamelCase()
    {
        var json = """{"sellerId":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","frozenUntil":"2026-06-01T00:00:00Z","reason":"ToS violation"}""";
        var result = JsonSerializer.Deserialize<SellerFrozenEvent>(json);
        Assert.NotNull(result);
        Assert.Equal(Guid.Parse("a1b2c3d4-e5f6-7890-abcd-ef1234567890"), result.SellerId);
        Assert.Equal("ToS violation", result.Reason);
    }

    [Fact]
    public void SellerFrozenEvent_DeserializesWithNullFields()
    {
        var json = """{"sellerId":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","frozenUntil":null,"reason":null}""";
        var result = JsonSerializer.Deserialize<SellerFrozenEvent>(json);
        Assert.NotNull(result);
        Assert.Null(result.FrozenUntil);
        Assert.Null(result.Reason);
    }

    [Fact]
    public void BundleActivatedEvent_DeserializesFromCamelCase()
    {
        var json = """{"id":"11111111-1111-1111-1111-111111111111","participantId":"22222222-2222-2222-2222-222222222222","sellerId":"33333333-3333-3333-3333-333333333333","bundleName":"Pro Plan","pricePaidCents":2999,"currency":"USD","durationDays":30}""";
        var result = JsonSerializer.Deserialize<BundleActivatedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal(Guid.Parse("11111111-1111-1111-1111-111111111111"), result.Id);
        Assert.Equal("Pro Plan", result.BundleName);
    }

    [Fact]
    public void ChargebackProcessedEvent_DeserializesFromCamelCase()
    {
        var json = """{"subscriptionId":"11111111-1111-1111-1111-111111111111","participantId":"22222222-2222-2222-2222-222222222222","lockedCents":5000,"chargebackCents":2000,"partial":true}""";
        var result = JsonSerializer.Deserialize<ChargebackProcessedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal(5000L, result.LockedCents);
        Assert.Equal(2000L, result.ChargebackCents);
        Assert.True(result.Partial);
    }

    [Fact]
    public void PaymentFailedEvent_DeserializesFromCamelCase()
    {
        var json = """{"userId":"11111111-1111-1111-1111-111111111111","subscriptionId":"22222222-2222-2222-2222-222222222222","attempt":2,"nextRetryAt":"2026-06-01T00:00:00Z"}""";
        var result = JsonSerializer.Deserialize<PaymentFailedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal(2, result.Attempt);
        Assert.NotNull(result.UserId);
        Assert.NotNull(result.SubscriptionId);
    }

    [Fact]
    public void PaymentFailedFinalEvent_DeserializesFromCamelCase()
    {
        var json = """{"userId":"11111111-1111-1111-1111-111111111111","subscriptionId":"22222222-2222-2222-2222-222222222222","attempts":3}""";
        var result = JsonSerializer.Deserialize<PaymentFailedFinalEvent>(json);
        Assert.NotNull(result);
        Assert.Equal(3, result.Attempts);
    }

    [Fact]
    public void PlanChangedEvent_DeserializesFromCamelCase()
    {
        var json = """{"userId":"11111111-1111-1111-1111-111111111111","newPlan":"Premium"}""";
        var result = JsonSerializer.Deserialize<PlanChangedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal("Premium", result.NewPlan);
    }

    [Fact]
    public void PlanChangedEvent_DeserializesWithNullNewPlan()
    {
        var json = """{"userId":"11111111-1111-1111-1111-111111111111","newPlan":null}""";
        var result = JsonSerializer.Deserialize<PlanChangedEvent>(json);
        Assert.NotNull(result);
        Assert.Null(result.NewPlan);
    }

    [Fact]
    public void PlatformActivatedEvent_DeserializesFromCamelCase()
    {
        var json = """{"userId":"11111111-1111-1111-1111-111111111111","planCode":"PREMIUM","status":"active"}""";
        var result = JsonSerializer.Deserialize<PlatformActivatedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal("PREMIUM", result.PlanCode);
        Assert.Equal("active", result.Status);
    }

    [Fact]
    public void PlatformActivatedEvent_DeserializesWithNullUserId()
    {
        var json = """{"userId":null,"planCode":"PREMIUM","status":"active"}""";
        var result = JsonSerializer.Deserialize<PlatformActivatedEvent>(json);
        Assert.NotNull(result);
        Assert.Null(result.UserId);
    }

    [Fact]
    public void RefundIssuedEvent_DeserializesFromCamelCase()
    {
        var json = """{"subscriptionId":"11111111-1111-1111-1111-111111111111","participantId":"22222222-2222-2222-2222-222222222222","amountCents":1500}""";
        var result = JsonSerializer.Deserialize<RefundIssuedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal(1500L, result.AmountCents);
    }

    [Fact]
    public void SellerClosedEvent_DeserializesFromCamelCase()
    {
        var json = """{"sellerId":"11111111-1111-1111-1111-111111111111","reason":"Business closed","from":"seller"}""";
        var result = JsonSerializer.Deserialize<SellerClosedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal("Business closed", result.Reason);
        Assert.Equal("seller", result.From);
    }

    [Fact]
    public void BundleExpiredEvent_DeserializesFromCamelCase()
    {
        var json = """{"subscriptionId":"11111111-1111-1111-1111-111111111111","reason":"expired"}""";
        var result = JsonSerializer.Deserialize<BundleExpiredEvent>(json);
        Assert.NotNull(result);
    }

    [Fact]
    public void ReceiptIssuedEvent_DeserializesFromCamelCase()
    {
        var json = """{"subscriptionId":"11111111-1111-1111-1111-111111111111","participantId":"22222222-2222-2222-2222-222222222222","amountCents":2999,"currency":"USD"}""";
        var result = JsonSerializer.Deserialize<ReceiptIssuedEvent>(json);
        Assert.NotNull(result);
    }

    [Fact]
    public void SellerBannedEvent_DeserializesFromCamelCase()
    {
        var json = """{"sellerId":"11111111-1111-1111-1111-111111111111","reason":"Fraud","bannedUntil":"2027-01-01T00:00:00Z"}""";
        var result = JsonSerializer.Deserialize<SellerBannedEvent>(json);
        Assert.NotNull(result);
        Assert.Equal("Fraud", result.Reason);
    }

    [Fact]
    public void RenewalPromptEvent_DeserializesFromCamelCase()
    {
        var json = """{"userId":"11111111-1111-1111-1111-111111111111","subscriptionId":"22222222-2222-2222-2222-222222222222","participantId":"33333333-3333-3333-3333-333333333333","amountCents":2999,"currency":"USD","reason":"renewal"}""";
        var result = JsonSerializer.Deserialize<RenewalPromptEvent>(json);
        Assert.NotNull(result);
        Assert.Equal(2999, result.AmountCents);
    }

    [Fact]
    public void PlatformExpiredEvent_DeserializesFromCamelCase()
    {
        var json = """{"userId":"11111111-1111-1111-1111-111111111111","planCode":"PREMIUM"}""";
        var result = JsonSerializer.Deserialize<PlatformExpiredEvent>(json);
        Assert.NotNull(result);
    }

    [Fact]
    public void SellerUnfrozenEvent_DeserializesFromCamelCase()
    {
        var json = """{"sellerId":"11111111-1111-1111-1111-111111111111","frozenUntil":null}""";
        var result = JsonSerializer.Deserialize<SellerUnfrozenEvent>(json);
        Assert.NotNull(result);
    }

    [Fact]
    public void SellerWindingDownEvent_DeserializesFromCamelCase()
    {
        var json = """{"sellerId":"11111111-1111-1111-1111-111111111111","reason":"Retiring","windingDownUntil":"2026-12-31T00:00:00Z"}""";
        var result = JsonSerializer.Deserialize<SellerWindingDownEvent>(json);
        Assert.NotNull(result);
    }
}
