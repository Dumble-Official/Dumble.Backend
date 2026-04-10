using System.Linq.Expressions;
using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.Specifications;

public abstract class BaseSpecifications<TEntity, TKey> : ISpecification<TEntity, TKey>
where TEntity : AggregateRoot<TKey>
where TKey : ValueObject
{
    public Expression<Func<TEntity, bool>> Criteria { get; set; } = default!;
    public List<Expression<Func<TEntity, object>>> Includes { get; set; } = default!;
    public bool IsPaginationEnabled { get; set; }
    public int Take { get; set; }
    public int Skip { get; set; }

    protected BaseSpecifications(Expression<Func<TEntity, bool>> criteria)
    {
        Criteria = criteria;
    }

    protected virtual void AddIncludes()
    {
        Includes = new();
    }

    protected virtual void AddPagination(int pageIndex, int pageSize)
    {
        IsPaginationEnabled = true;
        Take = pageSize;
        Skip = (pageIndex - 1) * pageSize;
    }
}