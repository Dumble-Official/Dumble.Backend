using System.Linq.Expressions;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.Specifications.Bundles;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetAllBundles;

internal sealed class GetAllBundlesQueryHandler(
    IGenericRepository<Bundle, BundleId> bundlesRepository) : IRequestHandler<GetAllBundlesQuery, GetAllBundlesResult>
{
    public async Task<GetAllBundlesResult> Handle(GetAllBundlesQuery request, CancellationToken cancellationToken)
    {
        var pageIndex = request.PageIndex < 1 ? 1 : request.PageIndex;
        var pageSize = request.PageSize is < 1 or > 100 ? 20 : request.PageSize;

        // Build the owner/category filter outside the expression so EF can
        // translate the value-object equality to a simple column comparison.
        // No owner filter → returns all bundles (the store). With an owner →
        // only that account's bundles (a gym/trainer profile).
        var ownerAccount = request.OwnerId is { } oid ? AccountId.Create(oid) : null;
        var categoryId = request.CategoryId is { } cid ? CategoryId.Create(cid) : null;

        Expression<Func<Bundle, bool>> criteria = b =>
            (ownerAccount == null || b.OwnerId == ownerAccount) &&
            (categoryId == null || b.CategoryId == categoryId);

        var spec = new GetAllBundlesSpecifications(pageIndex, pageSize, criteria);
        var bundles = await bundlesRepository.ListAsync(spec);
        var total = await bundlesRepository.Count(criteria);

        var items = bundles.Select(b => new BundleListItem(
            b.Id.Value,
            b.Images.Select(i => i.Value).ToList(),
            b.Name.Value,
            b.Description.Value,
            b.Price.Value,
            b.ExpiresOn,
            b.Status.ToString(),
            b.Viewers.Count,
            b.OwnerId.Value,
            b.OwnerType.ToString().ToUpperInvariant(),   // TRAINER | GYM
            b.OwnerUserId
        )).ToList();

        return new GetAllBundlesResult(items, total);
    }
}
