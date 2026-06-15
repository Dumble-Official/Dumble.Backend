using Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;
using Dumble.RecommendationService.Domain.Outbox;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.RecommendationService.Application.Features.Interactions;

/// <summary>
/// Translates the domain events the service consumes (Channel 2) into a
/// <see cref="RecordInteractionCommand"/>. The event id becomes the command's source id so
/// a redelivered event is buffered at most once. Pure, so it is unit-tested without a broker.
/// </summary>
public static class InteractionEventMapper
{
    public static RecordInteractionCommand FromPostReacted(PostReactedEvent e) =>
        new(e.ReactorId, e.PostId, InteractionSignal.Reaction, e.CreatedAt, SourceEventId: e.EventId.ToString());

    public static RecordInteractionCommand FromReactionRemoved(ReactionRemovedEvent e) =>
        // ReactionRemovedEvent carries no action time of its own, so use the event's occurrence time.
        new(e.ReactorId, e.PostId, InteractionSignal.ReactionRemoved, e.OccurredOn, SourceEventId: e.EventId.ToString());

    public static RecordInteractionCommand FromCommentCreated(CommentCreatedEvent e) =>
        new(e.CommentAuthorId, e.PostId, InteractionSignal.Comment, e.CreatedAt, SourceEventId: e.EventId.ToString());

    public static RecordInteractionCommand FromCommentDeleted(CommentDeletedEvent e) =>
        // CommentDeletedEvent carries no action time of its own, so use the event's occurrence time.
        new(e.CommentAuthorId, e.PostId, InteractionSignal.CommentRemoved, e.OccurredOn, SourceEventId: e.EventId.ToString());
}
