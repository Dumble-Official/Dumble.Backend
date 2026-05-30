using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using MediatR;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Channel 2: un-reacting removes the positive rating signal from Recombee.</summary>
public sealed class ReactionRemovedConsumer : IConsumer<ReactionRemovedEvent>
{
    private readonly ISender _sender;

    public ReactionRemovedConsumer(ISender sender) => _sender = sender;

    public Task Consume(ConsumeContext<ReactionRemovedEvent> context) =>
        _sender.Send(InteractionEventMapper.FromReactionRemoved(context.Message), context.CancellationToken);
}
