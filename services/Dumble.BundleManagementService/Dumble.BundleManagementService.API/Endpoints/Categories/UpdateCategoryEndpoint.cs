using Dumble.BundleManagementService.Application.Features.Categories.Commands.UpdateCategoryCommand;
using Dumble.BundleManagementService.Contracts.Categories.UpdateCategory;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Categories;

internal sealed class UpdateCategoryEndpoint(ISender mediator)
    : Endpoint<UpdateCategoryRequest, UpdateCategoryResponse>
{
    public override void Configure()
    {
        Put("/api/categories/{id}");
        Roles("ADMIN");
        Options(o => o.WithTags("Categories"));
    }

    public override async Task<UpdateCategoryResponse> ExecuteAsync(UpdateCategoryRequest req, CancellationToken ct)
    {
        var result = await mediator.Send(new UpdateCategoryCommand(req.Id, req.Name), ct);
        return new UpdateCategoryResponse(result.Id, result.Name);
    }
}
