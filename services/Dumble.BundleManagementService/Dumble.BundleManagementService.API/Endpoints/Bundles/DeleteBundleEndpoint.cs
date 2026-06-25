using System.Net;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;
using FastEndpoints;
using MediatR;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

// EndpointWithoutRequest: a DELETE carries no body, so binding an
// Endpoint<DeleteBundleRequest> made FastEndpoints try to JSON-parse the empty
// body and fail with "The input does not contain any JSON tokens" (400).
// We read the id straight from the route instead.
public sealed class DeleteBundleEndpoint(ISender mediator) : EndpointWithoutRequest
{
    public override void Configure()
    {
        Delete("/api/bundles/{id}");
        Roles("ADMIN", "GYM_OWNER", "GYM", "TRAINER");
        Options(x => x.WithTags("Bundles"));
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<Guid>("id");
        await mediator.Send(new DeleteBundleCommand(id), ct);
        await Send.ResponseAsync(new Void(), (int)HttpStatusCode.NoContent, ct);
    }
}
