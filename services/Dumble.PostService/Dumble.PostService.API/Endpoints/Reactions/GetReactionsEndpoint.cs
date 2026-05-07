using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Reactions.Queries.GetReactions;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.API.Endpoints.Reactions;

public class GetReactionsEndpoint(IMediator mediator) : EndpointWithoutRequest<ReactionsSummaryResponse>
{
    public override void Configure()
    {
        Get("/api/posts/{postId}/reactions");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var postId = Route<Guid>("postId");
        var currentUserId = User.Identity?.IsAuthenticated == true
            ? User.FindFirst("userId")?.Value
            : null;

        var result = await mediator.Send(new GetReactionsQuery(postId, currentUserId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
