namespace Dumble.BundleManagementService.Contracts.Bundles.UpdateBundle;

public sealed record UpdateBundleRequest(
    Guid Id,
    string? Name,
    string? Description,
    decimal? Price,
    string? Status,
    DateTime? ExpiresOn,
    Guid? CategoryId
);
