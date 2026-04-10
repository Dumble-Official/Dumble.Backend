using MediatR;

namespace Dumble.SocialService.Application.Features.Follows.Commands.FollowUser;

public record FollowUserCommand(string FollowerId, string FollowerName, string? FollowerImage, string FolloweeId)
    : IRequest;
