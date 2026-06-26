using Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetAllBundles;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Contracts.Bundles.GetAllBundles;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class GetAllBundlesEndpoint(ISender mediator)
    : EndpointWithoutRequest<GetAllBundlesResponse>
{
    public override void Configure()
    {
        Get("/api/bundles");
        AllowAnonymous();
        Options(o => o.WithTags("Bundles"));
    }

    public override async Task<GetAllBundlesResponse> ExecuteAsync(CancellationToken ct)
    {
        var pageIndex = Query<int?>("pageIndex", isRequired: false) ?? 1;
        var pageSize = Query<int?>("pageSize", isRequired: false) ?? 20;

        // ownerId is the external user/profile id; convert it the same way bundle
        // creation does so a profile only lists the bundles it actually owns.
        var ownerIdRaw = Query<string?>("ownerId", isRequired: false);
        Guid? ownerAccountId = string.IsNullOrWhiteSpace(ownerIdRaw)
            ? null
            : AccountIdentity.ToAccountGuid(ownerIdRaw);

        var categoryIdRaw = Query<string?>("categoryId", isRequired: false);
        Guid? categoryId = Guid.TryParse(categoryIdRaw, out var cid) ? cid : null;

        var result = await mediator.Send(
            new GetAllBundlesQuery(pageIndex, pageSize, ownerAccountId, categoryId), ct);

        var items = result.Items
            .Select(i => new BundleListItemResponse(
                i.Id, i.Images, i.Name, i.Description, i.Price, i.ExpiresOn, i.Status, i.ViewCount,
                i.SellerId, i.SellerType))
            .ToList();

        return new GetAllBundlesResponse(items, result.TotalCount);
    }
}
