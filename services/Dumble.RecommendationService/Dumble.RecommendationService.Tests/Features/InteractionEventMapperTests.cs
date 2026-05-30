using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.RecommendationService.Domain.Outbox;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Posts;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class InteractionEventMapperTests
{
    private static readonly DateTimeOffset When = new(2026, 5, 30, 1, 0, 0, TimeSpan.Zero);

    [Fact]
    public void PostReacted_maps_to_reaction_for_the_reactor()
    {
        var e = new PostReactedEvent("p1", "author1", "reactor1", "Reactor", null, default(ReactionType), When);

        var cmd = InteractionEventMapper.FromPostReacted(e);

        Assert.Equal("reactor1", cmd.UserId);
        Assert.Equal("p1", cmd.ItemId);
        Assert.Equal(InteractionSignal.Reaction, cmd.Signal);
        Assert.Equal(When, cmd.OccurredAt);
        Assert.Equal(e.EventId.ToString(), cmd.SourceEventId);
    }

    [Fact]
    public void ReactionRemoved_maps_to_rating_deletion_using_event_time()
    {
        var e = new ReactionRemovedEvent("p1", "author1", "reactor1");

        var cmd = InteractionEventMapper.FromReactionRemoved(e);

        Assert.Equal("reactor1", cmd.UserId);
        Assert.Equal("p1", cmd.ItemId);
        Assert.Equal(InteractionSignal.ReactionRemoved, cmd.Signal);
        Assert.Equal(e.OccurredOn, cmd.OccurredAt);
        Assert.Equal(e.EventId.ToString(), cmd.SourceEventId);
    }

    [Fact]
    public void CommentCreated_maps_to_comment_for_the_commenter()
    {
        var e = new CommentCreatedEvent("c1", "p1", "author1", "commenter1", "Commenter", null, null, "nice", When);

        var cmd = InteractionEventMapper.FromCommentCreated(e);

        Assert.Equal("commenter1", cmd.UserId);
        Assert.Equal("p1", cmd.ItemId);
        Assert.Equal(InteractionSignal.Comment, cmd.Signal);
        Assert.Equal(When, cmd.OccurredAt);
        Assert.Equal(e.EventId.ToString(), cmd.SourceEventId);
    }
}
