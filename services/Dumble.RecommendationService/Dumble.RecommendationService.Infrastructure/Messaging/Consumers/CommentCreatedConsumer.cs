using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using MediatR;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Channel 2: a comment is a strong engagement (bookmark); also harvests the
/// commenter's profile (name/avatar) for suggestion hydration.</summary>
public sealed class CommentCreatedConsumer : IConsumer<CommentCreatedEvent>
{
    private readonly ISender _sender;
    private readonly IUserProfileProjection _profiles;

    public CommentCreatedConsumer(ISender sender, IUserProfileProjection profiles)
    {
        _sender = sender;
        _profiles = profiles;
    }

    public async Task Consume(ConsumeContext<CommentCreatedEvent> context)
    {
        var e = context.Message;
        await _profiles.SetAsync(e.CommentAuthorId, e.CommenterName, e.CommenterImage, context.CancellationToken);
        await _sender.Send(InteractionEventMapper.FromCommentCreated(e), context.CancellationToken);
    }
}
