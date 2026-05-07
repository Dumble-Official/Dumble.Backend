using System.Runtime.InteropServices.JavaScript;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;
using Dumble.BundleManagementService.Contracts.Bundles.DeleteBundle;
using FastEndpoints;
using MediatR;
using Microsoft.AspNetCore.Http.HttpResults;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class DeleteBundleEndpoint(ISender mediator) : Endpoint<DeleteBundleRequest>
{
    public override void Configure()
    {
        Delete("/api/bundles");
        // Authenticated by default. DeleteBundleCommandHandler enforces
        // ownership (only the bundle's owner can delete it).
        Options(x => x.WithTags("Bundles")
            .Accepts<DeleteBundleRequest>("multipart/form-data"));
    }

    public override async Task<object?> ExecuteAsync(DeleteBundleRequest req, CancellationToken ct)
    {
        var command = new DeleteBundleCommand(
            req.Id
        );

        await mediator.Send(command, ct);
        
        return await Send.ResponseAsync(new Void(), 201, ct);
    }
}