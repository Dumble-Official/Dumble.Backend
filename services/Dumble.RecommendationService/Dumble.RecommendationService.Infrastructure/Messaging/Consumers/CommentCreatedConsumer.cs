using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using MediatR;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Channel 2: commenting on a post is a strong engagement, recorded as a bookmark.</summary>
public sealed class CommentCreatedConsumer : IConsumer<CommentCreatedEvent>
{
    private readonly ISender _sender;

    public CommentCreatedConsumer(ISender sender) => _sender = sender;

    public Task Consume(ConsumeContext<CommentCreatedEvent> context) =>
        _sender.Send(InteractionEventMapper.FromCommentCreated(context.Message), context.CancellationToken);
}
