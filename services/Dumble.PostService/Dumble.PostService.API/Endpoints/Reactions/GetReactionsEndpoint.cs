using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Reactions.Queries.GetReactions;
using Dumble.PostService.Contracts.Reactions;
using Dumble.SharedKernel.Contracts;

namespace Dumble.PostService.API.Endpoints.Reactions;

public class GetReactionsEndpoint : EndpointWithoutRequest<ReactionsSummaryResponse>
{
    private readonly IMediator _mediator;
    private readonly ILoggedInUserService _userService;

    public GetReactionsEndpoint(IMediator mediator, ILoggedInUserService userService)
    {
        _mediator = mediator;
        _userService = userService;
    }

    public override void Configure()
    {
        Get("/api/posts/{postId}/reactions");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var postId = Route<Guid>("postId");
        string? currentUserId = null;

        try
        {
            var user = await _userService.GetCurrentUserAsync(ct);
            currentUserId = user.Id;
        }
        catch
        {
            // Anonymous user — no current user reaction
        }

        var result = await _mediator.Send(new GetReactionsQuery(postId, currentUserId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
