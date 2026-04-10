using MediatR;

namespace Dumble.SocialService.Application.Features.Follows.Commands.UnfollowUser;

public record UnfollowUserCommand(string FollowerId, string FolloweeId) : IRequest;
