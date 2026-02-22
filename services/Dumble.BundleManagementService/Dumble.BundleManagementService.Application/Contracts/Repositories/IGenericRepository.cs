using System.Linq.Expressions;
using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Application.Contracts.Repositories;

public interface IGenericRepository<TEntity, TKey> : IAsyncDisposable
where TEntity : AggregateRoot<TKey>
where TKey : ValueObject
{
    public Task<IEnumerable<TEntity>> GetAll(bool asNoTracking = true);
    public Task<int> Count(Expression<Func<TEntity, bool>> criteria);
    public Task<TEntity?> Get(TKey id);
    public Task Create(TEntity entity);
    public void Update(TEntity entity);
    public void Delete(TEntity entity); 
    public Task<int> CompleteAsync(); 
}