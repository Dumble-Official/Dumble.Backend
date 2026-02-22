using Microsoft.AspNetCore.Http;

namespace Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;

public sealed record CreateBundleRequest(
        IFormFileCollection? Images,
        string Name,
        string Description,
        decimal Price,
        string Status,
        DateTime ExpiresOn,
        Guid CategoryId
        );