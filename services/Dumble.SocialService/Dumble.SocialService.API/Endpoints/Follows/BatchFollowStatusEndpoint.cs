using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Follows.Queries.GetBatchFollowStatus;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.API.Endpoints.Follows;

public class BatchFollowStatusEndpoint : Endpoint<BatchFollowStatusRequest, BatchFollowStatusResponse>
{
    private readonly IMediator _mediator;

    public BatchFollowStatusEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/social/follow/status/batch");
        Claims("userId");
    }

    public override async Task HandleAsync(BatchFollowStatusRequest req, CancellationToken ct)
    {
        var followerId = User.FindFirst("userId")!.Value;
        var result = await _mediator.Send(new GetBatchFollowStatusQuery(followerId, req.UserIds), ct);
        await SendAsync(result, cancellation: ct);
    }
}
