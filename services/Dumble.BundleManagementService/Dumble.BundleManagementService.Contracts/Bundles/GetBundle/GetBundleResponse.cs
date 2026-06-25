namespace Dumble.BundleManagementService.Contracts.Bundles.GetBundle;

public sealed record GetBundleResponse(
    Guid Id,
    IReadOnlyList<string> Images,
    string Name,
    string Description,
    decimal Price,
    DateTime ExpiresOn,
    string Status,
    int ViewCount,
    string Category,
    // Consumed by the Subscription service at checkout (its BundleSnapshot). The
    // app ignores the extra fields.
    Guid SellerId,
    string SellerType,
    long PriceCents,
    string Currency,
    int DurationDays,
    bool Active,
    IReadOnlyList<string> Amenities
);
