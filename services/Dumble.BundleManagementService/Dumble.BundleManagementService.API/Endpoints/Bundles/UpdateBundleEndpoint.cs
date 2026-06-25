using Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.UpdateBundle;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class UpdateBundleEndpoint(ISender mediator) : Endpoint<UpdateBundleRequest>
{
    public override void Configure()
    {
        Put("/api/bundles/{id}");
        Roles("ADMIN", "GYM_OWNER", "GYM", "TRAINER");
        Options(x => x.WithTags("Bundles"));
    }

    public override async Task<object?> ExecuteAsync(UpdateBundleRequest req, CancellationToken ct)
    {
        await mediator.Send(new UpdateBundleCommand(
            req.Id,
            req.Name,
            req.Description,
            req.Price,
            req.Status,
            req.ExpiresOn,
            req.CategoryId), ct);

        // Return 200 with a small body rather than 204 No Content. The API
        // gateway mishandles an empty downstream response and re-wraps it as a
        // generic "Gateway error: An unexpected error occurred" 500 — so the
        // client saw a failure even though the update had already persisted.
        return await Send.ResponseAsync(new { success = true }, 200, ct);
    }
}
