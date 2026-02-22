using Dumble.BundleManagementService.Application.Features.Categories.Queries.GetAllCategories;
using Dumble.BundleManagementService.Contracts.Categories.GetCategories;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Categories;

public sealed class GetAllCategoriesEndpoint(ISender mediator)
    : EndpointWithoutRequest<IEnumerable<GetAllCategoriesResponse>>
{
    public override void Configure()
    {
        Get("/api/categories"); // endpoint URL
        AllowAnonymous();
        Options(o => o.WithTags("Categories"));
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        // 1. Send the query via MediatR
        var categories = await mediator.Send(new GetAllCategoriesQuery(), ct);

        // 2. Map result to response DTO
        var response = categories.Select(c => new GetAllCategoriesResponse(c.Id, c.Name));

        await Send.OkAsync(response, ct);
    }
}