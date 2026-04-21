using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;
using FastEndpoints;
using MediatR;
using Microsoft.AspNetCore.Http.HttpResults;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

internal sealed class CreateBundleEndpoint(ISender mediator) : Endpoint<CreateBundleRequest>
{
    public override void Configure()
    {
        Post("/api/bundles");
        Claims("userId");
        Options(x => x.WithTags("Bundles")
            .Accepts<CreateBundleRequest>("multipart/form-data"));
    }

    public override async Task<object?> ExecuteAsync(CreateBundleRequest req, CancellationToken ct)
    {
        var createBundleCommand = new CreateBundleCommand(
            req.Images,
            req.Name,
            req.Description,
            req.Price,
            req.Status,
            req.ExpiresOn,
            req.CategoryId
        );

        await mediator.Send(createBundleCommand, ct);
        
        return await Send.ResponseAsync(new Void(), 201, ct);
    }
}