using MediatR;

namespace Dumble.SocialService.Application.Features.Behavior.Commands.TrackBehavior;

public record TrackBehaviorCommand(string UserId, string PostId, string EventType, string? EventData)
    : IRequest;
