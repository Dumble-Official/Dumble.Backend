using System.Net;
using Dumble.BundleManagementService.Application.Features.Categories.Commands.DeleteCategoryCommand;
using Dumble.BundleManagementService.Contracts.Categories.DeleteCategory;
using FastEndpoints;
using MediatR;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Categories;

internal sealed class DeleteCategoryEndpoint(ISender mediator) : Endpoint<DeleteCategoryRequest>
{
    public override void Configure()
    {
        Delete("/api/categories/{id}");
        Roles("ADMIN");
        Options(o => o.WithTags("Categories"));
    }

    public override async Task<object?> ExecuteAsync(DeleteCategoryRequest req, CancellationToken ct)
    {
        await mediator.Send(new DeleteCategoryCommand(req.Id), ct);
        return await Send.ResponseAsync(new Void(), (int)HttpStatusCode.NoContent, ct);
    }
}
