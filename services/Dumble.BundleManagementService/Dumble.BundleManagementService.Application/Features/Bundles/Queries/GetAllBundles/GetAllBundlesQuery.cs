using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetAllBundles;

// OwnerId/CategoryId are optional filters. OwnerId is the resolved account guid
// (the endpoint converts the external user id via AccountIdentity.ToAccountGuid),
// so a profile only ever lists the bundles that account actually owns.
public sealed record GetAllBundlesQuery(
    int PageIndex,
    int PageSize,
    Guid? OwnerId = null,
    Guid? CategoryId = null) : IRequest<GetAllBundlesResult>;

public sealed record GetAllBundlesResult(IReadOnlyList<BundleListItem> Items, int TotalCount);

public sealed record BundleListItem(
    Guid Id,
    IReadOnlyList<string> Images,
    string Name,
    string Description,
    decimal Price,
    DateTime ExpiresOn,
    string Status,
    int ViewCount,
    Guid SellerId,
    string SellerType);
