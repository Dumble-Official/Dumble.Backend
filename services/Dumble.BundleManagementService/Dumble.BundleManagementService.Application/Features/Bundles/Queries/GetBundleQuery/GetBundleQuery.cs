using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;

public sealed record GetBundleQuery(Guid Id, string? ViewerExternalId) : IRequest<GetBundleResult>;

public sealed record GetBundleResult(
    Guid Id,
    IReadOnlyList<string> Images,
    string Name,
    string Description,
    decimal Price,
    DateTime ExpiresOn,
    string Status,
    int ViewCount,
    string CategoryName,
    // Fields the Subscription service needs to snapshot a bundle at checkout
    // (it deserializes this response into its BundleSnapshot). Without these the
    // snapshot's seller/price/active fields are null/0/false and checkout fails
    // with "Bundle not available".
    Guid SellerId,
    string SellerType,
    long PriceCents,
    string Currency,
    int DurationDays,
    bool Active,
    IReadOnlyList<string> Amenities,
    // External auth user id of the seller — lets the app show the seller and
    // open their profile (SellerId is a non-addressable account hash).
    string? SellerUserId);
