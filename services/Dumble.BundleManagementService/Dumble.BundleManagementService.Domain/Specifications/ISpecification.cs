using System.Linq.Expressions;
using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.Specifications;

public interface ISpecification<TEntity, TKey>
    where TEntity : AggregateRoot<TKey>
    where TKey : ValueObject
{
    public Expression<Func<TEntity, bool>> Criteria { get; set; }
    public List<Expression<Func<TEntity, object>>> Includes { get; set; }
    public bool IsPaginationEnabled { get; set; }
    public int Take { get; set; }
    public int Skip { get; set; }
}