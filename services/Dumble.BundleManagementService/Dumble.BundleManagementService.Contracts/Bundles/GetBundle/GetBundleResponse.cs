namespace Dumble.BundleManagementService.Contracts.Bundles.GetBundle;

public sealed record GetBundleResponse(
        Guid Id,
        IEnumerable<string> Images,
        string Name,
        string Description,
        decimal Price,
        DateTime ExpiresOn,
        string Status,
        int ViewCount,
        string Category
        );