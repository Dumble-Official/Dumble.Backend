using System.Net;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;
using Dumble.BundleManagementService.Contracts.Bundles.DeleteBundle;
using FastEndpoints;
using MediatR;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class DeleteBundleEndpoint(ISender mediator) : Endpoint<DeleteBundleRequest>
{
    public override void Configure()
    {
        Delete("/api/bundles/{id}");
        Roles("ADMIN", "GYM_OWNER", "GYM", "TRAINER");
        Options(x => x.WithTags("Bundles"));
    }

    public override async Task<object?> ExecuteAsync(DeleteBundleRequest req, CancellationToken ct)
    {
        await mediator.Send(new DeleteBundleCommand(req.Id), ct);
        return await Send.ResponseAsync(new Void(), (int)HttpStatusCode.NoContent, ct);
    }
}
