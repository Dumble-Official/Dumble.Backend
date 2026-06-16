using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetAllBundles;

public sealed record GetAllBundlesQuery(int PageIndex, int PageSize) : IRequest<GetAllBundlesResult>;

public sealed record GetAllBundlesResult(IReadOnlyList<BundleListItem> Items, int TotalCount);

public sealed record BundleListItem(
    Guid Id,
    IReadOnlyList<string> Images,
    string Name,
    string Description,
    decimal Price,
    DateTime ExpiresOn,
    string Status,
    int ViewCount);
