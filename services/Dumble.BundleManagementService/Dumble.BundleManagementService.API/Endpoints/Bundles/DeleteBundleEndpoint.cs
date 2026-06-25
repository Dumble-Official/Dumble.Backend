using Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;
using FastEndpoints;
using MediatR;

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

        // Return 200 with a small body rather than 204 No Content — an empty
        // downstream response trips the API gateway into a generic 500.
        await Send.ResponseAsync(new { success = true }, 200, ct);
    }
}
