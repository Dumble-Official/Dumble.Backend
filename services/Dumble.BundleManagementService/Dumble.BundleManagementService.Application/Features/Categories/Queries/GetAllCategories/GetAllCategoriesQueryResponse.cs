namespace Dumble.BundleManagementService.Application.Features.Categories.Queries.GetAllCategories;

public sealed record GetAllCategoriesQueryResponse(
        string Id,
        string Name
    );