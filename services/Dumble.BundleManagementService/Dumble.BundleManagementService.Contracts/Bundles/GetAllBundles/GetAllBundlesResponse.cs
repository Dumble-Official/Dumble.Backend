namespace Dumble.BundleManagementService.Contracts.Bundles.GetAllBundles;

public sealed record GetAllBundlesResponse(
    IReadOnlyList<BundleListItemResponse> Items,
    int TotalCount);

public sealed record BundleListItemResponse(
    Guid Id,
    Guid OwnerId,
    IReadOnlyList<string> Images,
    string Name,
    string Description,
    decimal Price,
    DateTime ExpiresOn,
    string Status,
    int ViewCount);
