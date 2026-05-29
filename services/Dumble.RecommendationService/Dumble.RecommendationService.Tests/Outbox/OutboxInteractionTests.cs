using Dumble.RecommendationService.Domain.Outbox;
using Xunit;

namespace Dumble.RecommendationService.Tests.Outbox;

public class OutboxInteractionTests
{
    private static readonly DateTimeOffset T = new(2026, 5, 30, 12, 0, 0, TimeSpan.Zero);

    [Fact]
    public void Create_starts_pending_with_zero_attempts()
    {
        var i = OutboxInteraction.Create("u1", "p1", OutboxOperation.AddDetailView, T, T);

        Assert.Equal(OutboxStatus.Pending, i.Status);
        Assert.Equal(0, i.Attempts);
        Assert.Equal("u1", i.UserId);
        Assert.Equal("p1", i.ItemId);
        Assert.Equal(T, i.OccurredAt);
        Assert.Equal(T, i.CreatedAt);
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    public void Create_rejects_missing_userId(string? userId)
        => Assert.Throws<ArgumentException>(() =>
            OutboxInteraction.Create(userId!, "p1", OutboxOperation.AddDetailView, T, T));

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    public void Create_rejects_missing_itemId(string? itemId)
        => Assert.Throws<ArgumentException>(() =>
            OutboxInteraction.Create("u1", itemId!, OutboxOperation.AddDetailView, T, T));

    [Fact]
    public void Create_requires_rating_for_AddRating()
        => Assert.Throws<ArgumentException>(() =>
            OutboxInteraction.Create("u1", "p1", OutboxOperation.AddRating, T, T, ratingValue: null));

    [Fact]
    public void Create_normalises_blank_sourceEventId_to_null()
    {
        var i = OutboxInteraction.Create("u1", "p1", OutboxOperation.AddBookmark, T, T, sourceEventId: "   ");
        Assert.Null(i.SourceEventId);
    }

    [Fact]
    public void MarkSent_flips_status_to_sent()
    {
        var i = OutboxInteraction.Create("u1", "p1", OutboxOperation.AddDetailView, T, T);
        i.MarkSent();
        Assert.Equal(OutboxStatus.Sent, i.Status);
    }

    [Fact]
    public void RecordFailedAttempt_increments_attempts()
    {
        var i = OutboxInteraction.Create("u1", "p1", OutboxOperation.AddDetailView, T, T);
        i.RecordFailedAttempt();
        i.RecordFailedAttempt();
        Assert.Equal(2, i.Attempts);
    }
}
