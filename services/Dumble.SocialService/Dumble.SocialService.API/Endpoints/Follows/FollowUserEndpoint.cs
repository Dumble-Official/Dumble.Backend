using FastEndpoints;
using MediatR;
using Dumble.SharedKernel.Contracts;
using Dumble.SocialService.Application.Features.Follows.Commands.FollowUser;

namespace Dumble.SocialService.API.Endpoints.Follows;

public class FollowUserEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;
    private readonly ILoggedInUserService _userService;

    public FollowUserEndpoint(IMediator mediator, ILoggedInUserService userService)
    {
        _mediator = mediator;
        _userService = userService;
    }

    public override void Configure()
    {
        Post("/api/social/follow/{userId}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var targetUserId = Route<string>("userId")!;
        var currentUser = await _userService.GetCurrentUserAsync();

        await _mediator.Send(new FollowUserCommand(
            currentUser.Id, currentUser.DisplayName, currentUser.ProfileImage, targetUserId), ct);

        await SendNoContentAsync(ct);
    }
}
