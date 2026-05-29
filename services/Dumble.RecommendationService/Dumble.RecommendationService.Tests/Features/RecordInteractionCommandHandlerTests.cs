using Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;
using Dumble.RecommendationService.Domain.Outbox;
using Dumble.RecommendationService.Tests.TestDoubles;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class RecordInteractionCommandHandlerTests
{
    private static readonly DateTimeOffset Now = new(2026, 5, 30, 9, 0, 0, TimeSpan.Zero);
    private static readonly DateTimeOffset Occurred = new(2026, 5, 30, 8, 59, 0, TimeSpan.Zero);

    private static RecordInteractionCommandHandler HandlerWith(FakeInteractionOutboxWriter writer)
        => new(writer, new FixedTimeProvider(Now));

    [Fact]
    public async Task Builds_a_rating_interaction_from_a_reaction_signal()
    {
        var writer = new FakeInteractionOutboxWriter();

        var result = await HandlerWith(writer).Handle(
            new RecordInteractionCommand("u1", "p1", InteractionSignal.Reaction, Occurred, SourceEventId: "evt-1"),
            CancellationToken.None);

        Assert.True(result.Buffered);
        var captured = writer.Captured!;
        Assert.Equal(OutboxOperation.AddRating, captured.Operation);
        Assert.Equal(InteractionSignalMapper.PositiveRating, captured.RatingValue);
        Assert.Equal("evt-1", captured.SourceEventId);
        Assert.Equal(Occurred, captured.OccurredAt);   // original event time, not now
        Assert.Equal(Now, captured.CreatedAt);          // injected clock
        Assert.Null(captured.DurationSeconds);
    }

    [Fact]
    public async Task Keeps_duration_for_a_detail_view()
    {
        var writer = new FakeInteractionOutboxWriter();

        await HandlerWith(writer).Handle(
            new RecordInteractionCommand("u1", "p1", InteractionSignal.Dwell, Occurred, DurationSeconds: 42),
            CancellationToken.None);

        Assert.Equal(OutboxOperation.AddDetailView, writer.Captured!.Operation);
        Assert.Equal(42, writer.Captured!.DurationSeconds);
    }

    [Fact]
    public async Task Drops_duration_for_a_non_detail_view_signal()
    {
        var writer = new FakeInteractionOutboxWriter();

        await HandlerWith(writer).Handle(
            new RecordInteractionCommand("u1", "p1", InteractionSignal.Comment, Occurred, DurationSeconds: 42),
            CancellationToken.None);

        Assert.Null(writer.Captured!.DurationSeconds);
    }

    [Fact]
    public async Task Reports_not_buffered_when_the_writer_dedupes()
    {
        var writer = new FakeInteractionOutboxWriter { NextResult = false };

        var result = await HandlerWith(writer).Handle(
            new RecordInteractionCommand("u1", "p1", InteractionSignal.View, Occurred),
            CancellationToken.None);

        Assert.False(result.Buffered);
        Assert.Equal(1, writer.Calls);
    }
}
