using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using MediatR;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Channel 2: a reaction on a post becomes a positive rating interaction.</summary>
public sealed class PostReactedConsumer : IConsumer<PostReactedEvent>
{
    private readonly ISender _sender;

    public PostReactedConsumer(ISender sender) => _sender = sender;

    public Task Consume(ConsumeContext<PostReactedEvent> context) =>
        _sender.Send(InteractionEventMapper.FromPostReacted(context.Message), context.CancellationToken);
}
