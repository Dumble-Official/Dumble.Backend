using System.Net;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.UpdateBundle;
using FastEndpoints;
using MediatR;
using Void = FastEndpoints.Void;

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

        return await Send.ResponseAsync(new Void(), (int)HttpStatusCode.NoContent, ct);
    }
}
