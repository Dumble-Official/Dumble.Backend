using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using MediatR;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Channel 2: a deleted comment reverses the bookmark its creation recorded, so a comment the user
/// later removed stops counting as engagement in Recombee — the mirror of CommentCreated, like
/// ReactionRemoved mirrors PostReacted.
/// </summary>
public sealed class CommentDeletedConsumer : IConsumer<CommentDeletedEvent>
{
    private readonly ISender _sender;

    public CommentDeletedConsumer(ISender sender) => _sender = sender;

    public Task Consume(ConsumeContext<CommentDeletedEvent> context)
        => _sender.Send(InteractionEventMapper.FromCommentDeleted(context.Message), context.CancellationToken);
}
