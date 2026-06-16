using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.RecommendationService.Domain.Outbox;
using Dumble.SharedKernel.Events.Posts;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class CommentDeletedMappingTests
{
    [Fact]
    public void CommentRemoved_maps_to_DeleteBookmark()
    {
        var mapping = InteractionSignalMapper.Map(InteractionSignal.CommentRemoved);
        Assert.Equal(OutboxOperation.DeleteBookmark, mapping.Operation);
        Assert.Null(mapping.RatingValue);
    }

    [Fact]
    public void Comment_create_and_delete_are_inverse_signals()
    {
        Assert.Equal(OutboxOperation.AddBookmark, InteractionSignalMapper.Map(InteractionSignal.Comment).Operation);
        Assert.Equal(OutboxOperation.DeleteBookmark, InteractionSignalMapper.Map(InteractionSignal.CommentRemoved).Operation);
    }

    [Fact]
    public void FromCommentDeleted_targets_the_author_post_with_a_removal_signal()
    {
        var e = new CommentDeletedEvent("c1", "p1", "author1", "commenter1");

        var cmd = InteractionEventMapper.FromCommentDeleted(e);

        Assert.Equal("commenter1", cmd.UserId);
        Assert.Equal("p1", cmd.ItemId);
        Assert.Equal(InteractionSignal.CommentRemoved, cmd.Signal);
        Assert.Equal(e.EventId.ToString(), cmd.SourceEventId);
    }
}
