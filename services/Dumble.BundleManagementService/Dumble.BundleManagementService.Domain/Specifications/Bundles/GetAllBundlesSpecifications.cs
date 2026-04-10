using System.Linq.Expressions;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;

namespace Dumble.BundleManagementService.Domain.Specifications.Bundles;

public sealed class GetAllBundlesSpecifications : BaseSpecifications<Bundle, BundleId>
{
    public GetAllBundlesSpecifications(int pageIndex, int pageSize, Expression<Func<Bundle, bool>> criteria) : base(criteria)
    {
        AddPagination(pageIndex, pageSize);
    }
}