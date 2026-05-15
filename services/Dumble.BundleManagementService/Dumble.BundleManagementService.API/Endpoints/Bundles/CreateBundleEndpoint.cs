using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;
using FastEndpoints;
using MediatR;

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
        var images = req.Images?.Select(f => new UploadedImage(
                f.OpenReadStream(),
                f.FileName,
                f.ContentType))
            .ToList();

        var bundleId = await mediator.Send(new CreateBundleCommand(
            images,
            req.Name,
            req.Description,
            req.Price,
            req.Status,
            req.ExpiresOn,
            req.CategoryId), ct);

        await Send.CreatedAtAsync<GetBundleByIdEndpoint>(
            new { id = bundleId.Value },
            new CreateBundleResponse(bundleId.Value),
            cancellation: ct);
    }
}
