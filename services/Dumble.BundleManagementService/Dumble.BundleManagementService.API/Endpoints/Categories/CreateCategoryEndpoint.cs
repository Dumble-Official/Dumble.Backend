using Dumble.BundleManagementService.Application.Features.Categories.Commands.CreateCategoryCommand;
using Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;
using Dumble.BundleManagementService.Contracts.Categories.CreateCategory;
using FastEndpoints;
using MediatR;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Categories;

internal sealed class CreateCategoryEndpoint(ISender mediator) : Endpoint<CreateCategoryRequest>
{
    public override void Configure()
    {
        Post("/api/categories");
        AllowAnonymous();
        Options(x => x.WithTags("Categories"));
    }

    public override async Task<object?> ExecuteAsync(CreateCategoryRequest req, CancellationToken ct)
    {
        var command = new CreateCategoryCommand(
            req.Name
        );

        await mediator.Send(command, ct);
        
        return await Send.ResponseAsync(new Void(), 201, ct);
    }
}