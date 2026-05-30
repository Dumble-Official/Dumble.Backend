using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using MediatR;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Channel 2: a reaction becomes a positive rating interaction; also harvests the
/// reactor's profile (name/avatar) for suggestion hydration.</summary>
public sealed class PostReactedConsumer : IConsumer<PostReactedEvent>
{
    private readonly ISender _sender;
    private readonly IUserProfileProjection _profiles;

    public PostReactedConsumer(ISender sender, IUserProfileProjection profiles)
    {
        _sender = sender;
        _profiles = profiles;
    }

    public async Task Consume(ConsumeContext<PostReactedEvent> context)
    {
        var e = context.Message;
        await _profiles.SetAsync(e.ReactorId, e.ReactorName, e.ReactorImage, context.CancellationToken);
        await _sender.Send(InteractionEventMapper.FromPostReacted(e), context.CancellationToken);
    }
}
