using Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;
using FastEndpoints;
using MediatR;
using Microsoft.AspNetCore.Http;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

internal sealed class CreateBundleEndpoint(ISender mediator) : Endpoint<CreateBundleRequest, CreateBundleResponse>
{
    public override void Configure()
    {
        Post("/api/bundles");
        Roles("GYM_OWNER", "GYM", "TRAINER");
        Options(x => x.WithTags("Bundles")
            .Accepts<CreateBundleRequest>("multipart/form-data"));
    }

    public override async Task HandleAsync(CreateBundleRequest req, CancellationToken ct)
    {
        var bundleId = await mediator.Send(new CreateBundleCommand(
            req.Images,
            req.Name,
            req.Description,
            req.Price,
            req.Status,
            req.ExpiresOn,
            req.CategoryId), ct);

        HttpContext.Response.Headers.Location = $"/api/bundles/{bundleId.Value}";
        await Send.CreatedAtAsync<GetBundleByIdEndpoint>(
            new { id = bundleId.Value },
            new CreateBundleResponse(bundleId.Value),
            cancellation: ct);
    }
}
