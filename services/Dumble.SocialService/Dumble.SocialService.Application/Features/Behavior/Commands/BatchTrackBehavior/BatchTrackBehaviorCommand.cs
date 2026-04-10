using MediatR;
using Dumble.SocialService.Contracts.Behavior;

namespace Dumble.SocialService.Application.Features.Behavior.Commands.BatchTrackBehavior;

public record BatchTrackBehaviorCommand(string UserId, List<TrackBehaviorRequest> Events)
    : IRequest;
